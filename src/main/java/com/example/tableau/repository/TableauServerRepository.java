package com.example.tableau.repository;

import com.example.tableau.entity.TableauServer;
import com.example.tableau.enums.StatusFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableauServerRepository extends JpaRepository<TableauServer, Long> {

    Optional<TableauServer> findByAssetId(String assetId);

    List<TableauServer> findByStatusFlag(StatusFlag statusFlag);

    List<TableauServer> findByStatusFlagNot(StatusFlag statusFlag);

    @Modifying
    @Query("UPDATE TableauServer s SET s.statusFlag = :statusFlag WHERE s.assetId = :assetId")
    int updateStatusFlagByAssetId(@Param("assetId") String assetId, @Param("statusFlag") StatusFlag statusFlag);

    @Query("SELECT s FROM TableauServer s WHERE s.statusFlag != 'DELETED'")
    List<TableauServer> findAllActive();

    boolean existsByAssetId(String assetId);
}
